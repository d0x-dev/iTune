package com.darkxvenom.itune.ui.player

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.backend.formatMusicDuration
import com.darkxvenom.itune.backend.lyrics.LyricsHelper
import com.darkxvenom.itune.backend.lyrics.LyricsSearchResult
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

/** Formats milliseconds into signed seconds label (`+0.0s`, `+1.4s`, `-2.5s`). */
internal fun formatLyricsOffsetLabel(offsetMs: Long): String {
    val tenths = (offsetMs / 100.0).roundToInt()
    val seconds = tenths / 10f
    return if (seconds >= 0f) "+%.1fs".format(seconds) else "%.1fs".format(seconds)
}

@Composable
internal fun MorePopupLyricsMenuSection(
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenEdit: () -> Unit,
    onOpenOffset: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val offsetBadge = formatLyricsOffsetLabel(MusicPlayerState.lyricsOffsetMs)

    Column(modifier = Modifier.fillMaxWidth()) {
        PopupSubPageHeader(
            title = "Lyrics",
            trailingBadge = if (MusicPlayerState.hasCustomLyricsOverride) "Custom" else null,
            onBack = onBack
        )
        PopupMenuDivider()

        LyricsMenuActionRow(
            icon = Icons.Rounded.Search,
            title = "Search",
            trailingBadge = "Online",
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onOpenSearch()
            }
        )
        PopupMenuDivider()

        LyricsMenuActionRow(
            icon = Icons.Rounded.Edit,
            title = "Edit",
            trailingBadge = if (MusicPlayerState.hasCustomLyricsOverride) "Edited" else null,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onOpenEdit()
            }
        )
        PopupMenuDivider()

        LyricsMenuActionRow(
            icon = Icons.Rounded.Translate,
            title = "Translate",
            trailingBadge = "Later",
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                Toast.makeText(context, "Translation will be configured later", Toast.LENGTH_SHORT).show()
            }
        )
        PopupMenuDivider()

        LyricsMenuActionRow(
            icon = Icons.Rounded.Tune,
            title = "Lyrics offset",
            trailingBadge = offsetBadge,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onOpenOffset()
            }
        )
    }
}

@Composable
private fun LyricsMenuActionRow(
    icon: ImageVector,
    title: String,
    trailingBadge: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = TextStyle(
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Medium,
                fontSize = 15.5.sp,
                letterSpacing = (-0.15).sp
            ),
            color = Color.White.copy(alpha = 0.94f),
            modifier = Modifier.weight(1f)
        )
        if (!trailingBadge.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.14f))
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(
                    text = trailingBadge,
                    fontFamily = SFProDisplay,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.88f)
                )
            }
        }
    }
}

@Composable
internal fun MorePopupLyricsSearchSection(
    track: MusicTrack,
    onBack: () -> Unit,
    onLyricsSelected: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var songQuery by remember(track.id) { mutableStateOf(track.title) }
    var artistQuery by remember(track.id) { mutableStateOf(track.artists) }
    var searchNonce by remember(track.id) { mutableIntStateOf(1) }
    var isSearching by remember(track.id) { mutableStateOf(false) }
    var searchError by remember(track.id) { mutableStateOf<String?>(null) }
    var results by remember(track.id) { mutableStateOf<List<LyricsSearchResult>>(emptyList()) }

    fun triggerSearch() {
        keyboardController?.hide()
        searchNonce++
    }

    LaunchedEffect(track.id, searchNonce) {
        if (songQuery.isBlank() && artistQuery.isBlank()) {
            results = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        searchError = null
        try {
            val found = LyricsHelper.searchAllLyrics(
                title = songQuery,
                artist = artistQuery,
                durationSec = track.durationSec.toInt()
            )
            results = found
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            searchError = e.message?.takeIf { it.isNotBlank() } ?: "Lyrics search failed"
            results = emptyList()
        } finally {
            isSearching = false
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        PopupSubPageHeader(
            title = "Search Lyrics",
            trailingBadge = if (!isSearching && results.isNotEmpty()) "${results.size}" else null,
            onBack = onBack
        )
        PopupMenuDivider()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FrostedInputField(
                value = songQuery,
                onValueChange = { songQuery = it },
                label = "Song Name",
                placeholder = "Enter song title",
                onImeSearch = ::triggerSearch
            )
            FrostedInputField(
                value = artistQuery,
                onValueChange = { artistQuery = it },
                label = "Artist Name",
                placeholder = "Enter artist name",
                onImeSearch = ::triggerSearch
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.90f))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            triggerSearch()
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Search",
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            color = Color.Black
                        )
                    }
                }
            }
        }

        PopupMenuDivider()

        when {
            isSearching -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Searching lyrics…",
                            fontFamily = SFProDisplay,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.68f)
                        )
                    }
                }
            }

            results.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = searchError ?: "No lyrics found. Try refining the song or artist name.",
                        fontFamily = SFProDisplay,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.66f)
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                ) {
                    itemsIndexed(results) { index, item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    MusicPlayerState.applyCustomLyrics(track, item.rawText)
                                    MusicPlayerState.queueOpen = false
                                    MusicPlayerState.lyricsOpen = true
                                    Toast.makeText(context, "Lyrics applied (${item.provider})", Toast.LENGTH_SHORT).show()
                                    onLyricsSelected()
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = item.trackName.ifBlank { track.title },
                                    fontFamily = SFProDisplay,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.5.sp,
                                    color = Color.White.copy(alpha = 0.96f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (item.isSynced) Color(0xFFFA2D48).copy(alpha = 0.25f)
                                            else Color.White.copy(alpha = 0.14f)
                                        )
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = if (item.isSynced) "SYNCED" else "PLAIN",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.isSynced) Color(0xFFFF6D7F) else Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            val dur = if (item.durationSec > 0) " · ${formatMusicDuration(item.durationSec.toLong())}" else ""
                            Text(
                                text = "${item.artistName.ifBlank { track.artists }} · ${item.provider}$dur",
                                fontFamily = SFProDisplay,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.58f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (item.preview.isNotBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = item.preview,
                                    fontFamily = SFProDisplay,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.72f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (index < results.lastIndex) {
                            PopupMenuDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MorePopupLyricsEditSection(
    track: MusicTrack,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val initialText = remember(track.id) {
        val raw = MusicPlayerState.rawLyricsText.trim()
        val candidate = if (raw.isNotEmpty() && !raw.equals("null", ignoreCase = true)) {
            raw
        } else {
            LyricsHelper.formatToEditableText(MusicPlayerState.lyrics)
        }
        if (candidate.equals("null", ignoreCase = true)) "" else candidate
    }
    var draftText by remember(track.id) { mutableStateOf(initialText) }
    val hasTimestamps = remember(draftText) {
        draftText.contains(Regex("\\[\\d{1,3}:\\d{2}"))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        PopupSubPageHeader(
            title = "Edit Lyrics",
            trailingBadge = if (hasTimestamps) "Synced LRC" else "Plain Text",
            onBack = onBack
        )
        PopupMenuDivider()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 230.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                if (draftText.isEmpty()) {
                    Text(
                        text = "Write or paste lyrics here…\nSupports plain lines or [mm:ss.xx] timestamps.",
                        fontFamily = SFProDisplay,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                        color = Color.White.copy(alpha = 0.36f)
                    )
                }
                BasicTextField(
                    value = draftText,
                    onValueChange = { draftText = it },
                    textStyle = TextStyle(
                        fontFamily = SFProDisplay,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        color = Color.White.copy(alpha = 0.94f)
                    ),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.14f))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            draftText = ""
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Clear",
                        fontFamily = SFProDisplay,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }

                if (MusicPlayerState.hasCustomLyricsOverride) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFFA2D48).copy(alpha = 0.22f))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                MusicPlayerState.resetCustomLyrics(track)
                                Toast.makeText(context, "Restored online lyrics", Toast.LENGTH_SHORT).show()
                                onSaved()
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Reset",
                            fontFamily = SFProDisplay,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFF6D7F)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.92f))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            MusicPlayerState.applyCustomLyrics(track, draftText)
                            MusicPlayerState.queueOpen = false
                            MusicPlayerState.lyricsOpen = true
                            Toast.makeText(context, "Lyrics saved", Toast.LENGTH_SHORT).show()
                            onSaved()
                        }
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Save",
                        fontFamily = SFProDisplay,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

@Composable
internal fun MorePopupLyricsOffsetSection(
    onBack: () -> Unit
) {
    val view = LocalView.current
    val persistedOffsetMs = MusicPlayerState.lyricsOffsetMs
    var isSliding by remember { mutableStateOf(false) }
    var sliderSeconds by remember(persistedOffsetMs) {
        mutableFloatStateOf((persistedOffsetMs / 1000f).coerceIn(-5f, 5f))
    }

    val displayMs = if (isSliding) (sliderSeconds * 10f).roundToInt() * 100L else persistedOffsetMs
    val offsetLabel = formatLyricsOffsetLabel(displayMs)

    fun applySnappedSeconds(rawSec: Float) {
        val snappedMs = ((rawSec.coerceIn(-5f, 5f) * 10f).roundToInt()) * 100L
        sliderSeconds = snappedMs / 1000f
        MusicPlayerState.setLyricsOffset(snappedMs)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        PopupSubPageHeader(
            title = "Lyrics Offset",
            trailingBadge = offsetLabel,
            onBack = onBack
        )
        PopupMenuDivider()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = offsetLabel,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Slide forward for faster lyrics · backward for slower · Audio unaffected",
                fontFamily = SFProDisplay,
                fontSize = 11.5.sp,
                color = Color.White.copy(alpha = 0.62f)
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Exact same iOS capsule seek slider used in the player
            IosCapsuleSeekSlider(
                value = if (isSliding) sliderSeconds else (persistedOffsetMs / 1000f).coerceIn(-5f, 5f),
                valueRange = -5f..5f,
                isSeeking = isSliding,
                onSeekStart = { v ->
                    isSliding = true
                    applySnappedSeconds(v)
                },
                onSeekChange = { v ->
                    applySnappedSeconds(v)
                },
                onSeekEnd = { v ->
                    applySnappedSeconds(v)
                    isSliding = false
                }
            )

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "-5.0s",
                    fontFamily = SFProDisplay,
                    fontSize = 11.5.sp,
                    color = Color.White.copy(alpha = 0.56f)
                )
                Text(
                    text = "+0.0s",
                    fontFamily = SFProDisplay,
                    fontSize = 11.5.sp,
                    color = Color.White.copy(alpha = 0.56f)
                )
                Text(
                    text = "+5.0s",
                    fontFamily = SFProDisplay,
                    fontSize = 11.5.sp,
                    color = Color.White.copy(alpha = 0.56f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OffsetStepChip("-0.5s") {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    applySnappedSeconds((persistedOffsetMs - 500L) / 1000f)
                }
                OffsetStepChip("Reset (+0.0s)", highlighted = persistedOffsetMs != 0L) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    applySnappedSeconds(0f)
                }
                OffsetStepChip("+0.5s") {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    applySnappedSeconds((persistedOffsetMs + 500L) / 1000f)
                }
            }
        }
    }
}

@Composable
private fun OffsetStepChip(
    label: String,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (highlighted) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.12f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontFamily = SFProDisplay,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.94f)
        )
    }
}

@Composable
private fun FrostedInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    onImeSearch: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            fontFamily = SFProDisplay,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 4.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(11.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontFamily = SFProDisplay,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.35f)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = SFProDisplay,
                    fontSize = 14.sp,
                    color = Color.White
                ),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onImeSearch() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
