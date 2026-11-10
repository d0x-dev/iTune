package com.darkxvenom.itune.ui.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.darkxvenom.itune.backend.MusicArtist
import com.darkxvenom.itune.backend.MusicPlaylistRef
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.backend.UnifiedSearchResults
import com.darkxvenom.itune.backend.highQualityThumbnail
import com.darkxvenom.itune.ui.artist.components.ExplicitBadge
import com.darkxvenom.itune.ui.artist.components.ShimmerBox
import com.darkxvenom.itune.ui.artist.components.SongRowSkeleton
import com.darkxvenom.itune.ui.artist.theme.rememberArtworkPalette
import com.darkxvenom.itune.ui.components.AnimatedEqualizer
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleTextSecondary

internal enum class SearchResultFilter(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    PLAYLISTS("Playlists")
}

internal enum class TopResultBusyAction {
    ARTIST_SHUFFLE,
    ARTIST_MIX
}

internal sealed interface TopResultItem {
    val stableKey: String
    val title: String
    val thumbnailUrl: String?

    data class Artist(val artist: MusicArtist) : TopResultItem {
        override val stableKey: String get() = "artist_${artist.id}"
        override val title: String get() = artist.name
        override val thumbnailUrl: String? get() = artist.thumbnailUrl
    }

    data class Song(val track: MusicTrack) : TopResultItem {
        override val stableKey: String get() = "song_${track.id}"
        override val title: String get() = track.title
        override val thumbnailUrl: String? get() = track.thumbnailUrl
    }

    data class Album(val album: MusicPlaylistRef) : TopResultItem {
        override val stableKey: String get() = "album_${album.id}"
        override val title: String get() = album.title
        override val thumbnailUrl: String? get() = album.thumbnailUrl
    }

    data class Playlist(val playlist: MusicPlaylistRef) : TopResultItem {
        override val stableKey: String get() = "playlist_${playlist.id}"
        override val title: String get() = playlist.title
        override val thumbnailUrl: String? get() = playlist.thumbnailUrl
    }
}

internal data class SearchSuggestionRowModel(
    val text: String,
    val isHistory: Boolean
)

/**
 * Matches the user's written query across ALL four result categories (Artists, Songs, Albums, Playlists).
 * Exact spelling matches take absolute priority and return strictly ONE hero Top Result card.
 */
internal fun pickTopResult(query: String, results: UnifiedSearchResults): TopResultItem? {
    val qNorm = normalizeSearchText(query)
    if (qNorm.isEmpty() || results.isEmpty) return null
    val qStripped = stripPunctuation(qNorm)
    val qTokens = qStripped.split(' ').filter { it.isNotEmpty() }

    data class Scored(val item: TopResultItem, val score: Int)
    val candidates = ArrayList<Scored>(
        results.artists.size + results.songs.size + results.albums.size + results.playlists.size
    )

    fun scoreText(
        primaryTitle: String,
        secondaryText: String,
        bucketPriority: Int,
        index: Int
    ): Int {
        val normTitle = normalizeSearchText(primaryTitle)
        if (normTitle.isEmpty()) return 0
        val strippedTitle = stripPunctuation(normTitle)
        val coreTitle = stripSuffixTags(normTitle)
        val posPenalty = index.coerceAtMost(12) * 4

        // 1. Exact spelling match with user query (highest priority across Songs, Artists, Albums, Playlists)
        if (normTitle == qNorm) {
            return 2200 + bucketPriority - posPenalty
        }
        // 2. Punctuation-normalized exact match (e.g. apostrophes / hyphens / diacritics spacing)
        if (qStripped.isNotEmpty() && strippedTitle == qStripped) {
            return 1900 + bucketPriority - posPenalty
        }
        // 3. Exact core title match ignoring parenthetical suffixes like "(Official Video)" or "- Single"
        if (qStripped.isNotEmpty() && coreTitle == qStripped) {
            return 1650 + bucketPriority - posPenalty
        }
        // 4. Whole-word prefix match
        if (normTitle.startsWith("$qNorm ") || normTitle.startsWith(qNorm)) {
            val lengthDiff = (normTitle.length - qNorm.length).coerceIn(0, 120)
            return 1250 + bucketPriority - lengthDiff - posPenalty
        }
        // 5. Query tokens all match title (+ secondary artist/author context)
        if (qTokens.isNotEmpty()) {
            val combined = "$strippedTitle ${stripPunctuation(secondaryText)}".trim()
            if (qTokens.all { token -> containsWholeToken(strippedTitle, token) }) {
                val lengthDiff = (strippedTitle.length - qStripped.length).coerceIn(0, 140)
                return 920 + bucketPriority - lengthDiff - posPenalty
            }
            if (qTokens.size >= 2 && qTokens.all { token -> containsWholeToken(combined, token) }) {
                return 720 + bucketPriority - posPenalty
            }
            val matchedTokens = qTokens.count { token -> combined.contains(token) }
            if (matchedTokens == qTokens.size) {
                return 540 + bucketPriority - posPenalty
            }
        }
        return 0
    }

    results.artists.forEachIndexed { idx, artist ->
        val established = isEstablishedArtist(artist.subscribers)
        val artistPriority = if (established) 34 else -60
        val score = scoreText(
            primaryTitle = artist.name,
            secondaryText = "",
            bucketPriority = artistPriority,
            index = idx
        )
        if (score > 0) candidates.add(Scored(TopResultItem.Artist(artist), score))
    }
    results.songs.forEachIndexed { idx, track ->
        val score = scoreText(
            primaryTitle = track.title,
            secondaryText = track.artists,
            bucketPriority = 26,
            index = idx
        )
        if (score > 0) candidates.add(Scored(TopResultItem.Song(track), score))
    }
    results.albums.forEachIndexed { idx, album ->
        val score = scoreText(
            primaryTitle = album.title,
            secondaryText = album.author,
            bucketPriority = 20,
            index = idx
        )
        if (score > 0) candidates.add(Scored(TopResultItem.Album(album), score))
    }
    results.playlists.forEachIndexed { idx, playlist ->
        val score = scoreText(
            primaryTitle = playlist.title,
            secondaryText = playlist.author,
            bucketPriority = 14,
            index = idx
        )
        if (score > 0) candidates.add(Scored(TopResultItem.Playlist(playlist), score))
    }

    val best = candidates.maxByOrNull { it.score }
    if (best != null && best.score >= 500) {
        return best.item
    }
    return results.songs.firstOrNull()?.let(TopResultItem::Song)
        ?: results.artists.firstOrNull()?.let(TopResultItem::Artist)
        ?: results.albums.firstOrNull()?.let(TopResultItem::Album)
        ?: results.playlists.firstOrNull()?.let(TopResultItem::Playlist)
}

private val WHITESPACE_REGEX = Regex("\\s+")
private val NON_WORD_REGEX = Regex("[^\\p{L}\\p{Nd}\\s]")
private val PARENTHETICAL_SUFFIX_REGEX = Regex("\\s*[(\\[\\-–|].*$")
private val SCALE_NUMBER_REGEX = Regex("([\\d.,]+)\\s*([KMB])?", RegexOption.IGNORE_CASE)

/**
 * Distinguishes real music artists ("9.49M monthly audience", "120K subscribers") from tiny
 * unverified YouTube channels ("26 subscribers") that happen to share a hit song's exact name.
 */
private fun isEstablishedArtist(subscribers: String?): Boolean {
    if (subscribers.isNullOrBlank()) return false
    val lower = subscribers.lowercase()
    if (lower.contains("monthly") || lower.contains("audience") || lower.contains("listener")) {
        return true
    }
    val match = SCALE_NUMBER_REGEX.find(subscribers) ?: return false
    val number = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return false
    val suffix = match.groupValues[2].uppercase()
    return when (suffix) {
        "M", "B" -> true
        "K" -> number >= 5.0
        else -> number >= 5000.0
    }
}

private fun normalizeSearchText(text: String): String =
    text.trim().lowercase().replace(WHITESPACE_REGEX, " ")

private fun stripPunctuation(text: String): String =
    text.lowercase()
        .replace(NON_WORD_REGEX, " ")
        .trim()
        .replace(WHITESPACE_REGEX, " ")

private fun stripSuffixTags(normText: String): String =
    stripPunctuation(normText.replace(PARENTHETICAL_SUFFIX_REGEX, ""))

private fun containsWholeToken(haystack: String, token: String): Boolean {
    if (token.isEmpty()) return false
    var idx = haystack.indexOf(token)
    while (idx >= 0) {
        val beforeOk = idx == 0 || haystack[idx - 1] == ' '
        val endIdx = idx + token.length
        val afterOk = endIdx == haystack.length || haystack[endIdx] == ' '
        if (beforeOk && afterOk) return true
        idx = haystack.indexOf(token, idx + 1)
    }
    return false
}

/**
 * Colorful hero Top Result card matching either the Artist layout (circular avatar, > arrow, Shuffle + Mix pills)
 * or Song/Album/Playlist layout (rounded square cover, saved/Explicit + type metadata, 3-dot menu, Play + Save pills).
 * Each Top Result card derives its vibrant gradient theme dynamically from the artwork thumbnail.
 */
@Composable
internal fun TopResultHeroCard(
    item: TopResultItem,
    isCurrentSong: Boolean,
    isPlaying: Boolean,
    isSaved: Boolean,
    busyAction: TopResultBusyAction?,
    onCardClick: () -> Unit,
    onArtistShuffleClick: () -> Unit,
    onArtistMixClick: () -> Unit,
    onPlayClick: () -> Unit,
    onSaveToggleClick: () -> Unit,
    onMoreClick: (anchorCenterWindow: Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val highResUrl = remember(item.thumbnailUrl) {
        item.thumbnailUrl?.highQualityThumbnail() ?: item.thumbnailUrl
    }
    val palette = rememberArtworkPalette(imageUrl = highResUrl, dark = true)

    // Blend vibrant thumbnail accent into elevated/wash tones so every artwork gets a distinct colorful card
    val topLeftColor = lerp(palette.elevated, palette.accent, 0.36f).clampLuminance(maxLum = 0.19f)
    val midColor = lerp(palette.wash, palette.accent, 0.22f).clampLuminance(maxLum = 0.14f)
    val bottomRightColor = lerp(palette.background, palette.wash, 0.58f).clampLuminance(maxLum = 0.10f)
    val cardGradient = Brush.linearGradient(
        colors = listOf(topLeftColor, midColor, bottomRightColor),
        start = Offset.Zero,
        end = Offset(950f, 520f)
    )
    val cardRimBrush = Brush.linearGradient(
        colors = listOf(
            palette.accent.copy(alpha = 0.50f),
            Color.White.copy(alpha = 0.14f)
        )
    )
    var moreBtnCenter by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardGradient)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            palette.accent.copy(alpha = 0.28f),
                            palette.accent.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.14f, size.height * 0.25f),
                        radius = size.width * 0.72f
                    )
                )
            }
            .border(width = 0.9.dp, brush = cardRimBrush, shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onCardClick)
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val artShape = if (item is TopResultItem.Artist) CircleShape else RoundedCornerShape(9.dp)
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(artShape)
                        .background(Color.Black.copy(alpha = 0.28f))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(highResUrl)
                            .memoryCacheKey("top_result_${item.stableKey}")
                            .crossfade(180)
                            .build(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isCurrentSong) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedEqualizer(
                                isPlaying = isPlaying,
                                color = Color.White,
                                size = 22.dp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TopResultSubtitleRow(item = item, isSaved = isSaved)
                }

                Spacer(modifier = Modifier.width(4.dp))

                when (item) {
                    is TopResultItem.Artist -> {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Open artist",
                            tint = Color.White.copy(alpha = 0.90f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    else -> {
                        IconButton(
                            onClick = { onMoreClick(moreBtnCenter) },
                            modifier = Modifier
                                .size(38.dp)
                                .onGloballyPositioned { coords ->
                                    if (coords.isAttached) {
                                        val pos = coords.positionInWindow()
                                        moreBtnCenter = Offset(
                                            x = pos.x + coords.size.width / 2f,
                                            y = pos.y + coords.size.height / 2f
                                        )
                                    }
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = Color.White.copy(alpha = 0.90f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (item) {
                    is TopResultItem.Artist -> {
                        // Primary solid white Shuffle pill
                        TopResultActionPill(
                            label = "Shuffle",
                            icon = Icons.Rounded.Shuffle,
                            filledWhite = true,
                            isLoading = busyAction == TopResultBusyAction.ARTIST_SHUFFLE,
                            onClick = onArtistShuffleClick,
                            modifier = Modifier.weight(1f)
                        )
                        // Secondary translucent glass Mix pill
                        TopResultActionPill(
                            label = "Mix",
                            icon = Icons.Rounded.Sensors,
                            filledWhite = false,
                            isLoading = busyAction == TopResultBusyAction.ARTIST_MIX,
                            onClick = onArtistMixClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    else -> {
                        // Primary solid white Play pill
                        TopResultActionPill(
                            label = "Play",
                            icon = Icons.Rounded.PlayArrow,
                            filledWhite = true,
                            isLoading = false,
                            onClick = onPlayClick,
                            modifier = Modifier.weight(1f)
                        )
                        // Secondary translucent glass Save / Saved pill
                        TopResultActionPill(
                            label = if (isSaved) "Saved" else "Save",
                            icon = if (isSaved) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.PlaylistAdd,
                            filledWhite = false,
                            isLoading = false,
                            onClick = onSaveToggleClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

