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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.backend.formatMusicDuration
import com.darkxvenom.itune.backend.lyrics.LyricsHelper
import com.darkxvenom.itune.backend.lyrics.LyricsSearchResult
import com.darkxvenom.itune.backend.lyrics.LyricsTranslationHelper
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.ui.settings.AppleGlassSwitch
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
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
    track: MusicTrack,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenEdit: () -> Unit,
    onOpenOffset: () -> Unit,
    onOpenTranslate: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val offsetBadge = formatLyricsOffsetLabel(MusicPlayerState.lyricsOffsetMs)
    val songTranslations by LyricsTranslationHelper.currentSongTranslations.collectAsStateWithLifecycle()
    val isTranslatedForSong = songTranslations.containsKey(track.id) ||
        LyricsTranslationHelper.hasCachedTranslation(context, track.id)

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
            trailingBadge = if (isTranslatedForSong) "Active" else "AI",
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onOpenTranslate()
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

@Composable
internal fun MorePopupLyricsTranslateSection(
    track: MusicTrack,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val settingsState by SettingsStore.state.collectAsStateWithLifecycle()
    val translationStatus by LyricsTranslationHelper.status.collectAsStateWithLifecycle()
    val songTranslations by LyricsTranslationHelper.currentSongTranslations.collectAsStateWithLifecycle()

    val isTranslating = translationStatus is LyricsTranslationHelper.TranslationStatus.Translating
    val isTranslatedForSong = songTranslations.containsKey(track.id) ||
        LyricsTranslationHelper.hasCachedTranslation(context, track.id, settingsState.lyricsTranslateLanguage)

    var customPromptText by remember(settingsState.lyricsTranslationCustomPrompt) {
        mutableStateOf(settingsState.lyricsTranslationCustomPrompt)
    }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val hasApiKey = settingsState.lyricsTranslationApiKey.isNotBlank()
    val rawLyricsLines: List<String> = remember(track.id, MusicPlayerState.lyrics, MusicPlayerState.rawLyricsText) {
        val parsed = MusicPlayerState.lyrics.map { it.text.trim() }.filter { it.isNotEmpty() }
        if (parsed.isNotEmpty()) parsed else MusicPlayerState.rawLyricsText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        PopupSubPageHeader(
            title = "Translate Lyrics",
            trailingBadge = if (isTranslatedForSong) "Active" else null,
            onBack = onBack
        )
        PopupMenuDivider()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Track Info Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            fontFamily = SFProDisplay,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = track.artists,
                            fontFamily = SFProDisplay,
                            fontSize = 12.sp,
                            color = AppleTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isTranslatedForSong) Color(0xFF34C759).copy(alpha = 0.22f)
                                else Color.White.copy(alpha = 0.12f)
                            )
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isTranslatedForSong) "Translated" else "Original",
                            fontFamily = SFProDisplay,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isTranslatedForSong) Color(0xFF34C759) else AppleTextSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Target Language Row
            Text(
                text = "TARGET LANGUAGE",
                fontFamily = SFProDisplay,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.6f),
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
            )
            val currentLangName = LyricsTranslationHelper.AiTranslationLanguages[settingsState.lyricsTranslateLanguage]
                ?: settingsState.lyricsTranslateLanguage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        showLanguageDialog = true
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = null,
                        tint = Color(0xFF0A84FF),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = currentLangName,
                        fontFamily = SFProDisplay,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = AppleTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Inbuilt Prompt Types / Mode
            Text(
                text = "PROMPT TYPE",
                fontFamily = SFProDisplay,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.6f),
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val modes = listOf("Literal", "Hinglish", "Romanized", "Transcribed", "Custom")
                modes.forEach { mode ->
                    val isSelected = settingsState.lyricsTranslateMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isSelected) AppleMusicRed
                                else Color.White.copy(alpha = 0.10f)
                            )
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                SettingsStore.setLyricsTranslateMode(context, mode)
                            }
                            .padding(horizontal = 13.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = mode,
                            fontFamily = SFProDisplay,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.82f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // AI Provider selector
            Text(
                text = "AI ENGINE",
                fontFamily = SFProDisplay,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.6f),
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val providers = listOf(
                    LyricsTranslationHelper.PROVIDER_OPENROUTER to "OpenRouter",
                    LyricsTranslationHelper.PROVIDER_GEMINI to "Gemini",
                    LyricsTranslationHelper.PROVIDER_OPENAI to "OpenAI",
                    LyricsTranslationHelper.PROVIDER_CLAUDE to "Claude",
                )
                providers.forEach { (provId, provLabel) ->
                    val isSelected = settingsState.lyricsTranslationProvider == provId
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isSelected) Color(0xFF007AFF)
                                else Color.White.copy(alpha = 0.10f)
                            )
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                if (settingsState.lyricsTranslationProvider != provId) {
                                    // A model id / URL left over from another provider answers 404.
                                    SettingsStore.setLyricsTranslationModel(context, "")
                                    SettingsStore.setLyricsTranslationBaseUrl(context, "")
                                }
                                SettingsStore.setLyricsTranslationProvider(context, provId)
                            }
                            .padding(horizontal = 13.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = provLabel,
                            fontFamily = SFProDisplay,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.82f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "MODEL",
                fontFamily = SFProDisplay,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.6f),
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val savedModel = settingsState.lyricsTranslationModel
                val modelOptions = buildList {
                    add("" to "Default")
                    LyricsTranslationHelper.suggestedModels(settingsState.lyricsTranslationProvider)
                        .forEach { add(it to it.substringAfterLast('/')) }
                    // A custom id picked in Settings should stay visible as a chip.
                    if (savedModel.isNotBlank() && none { it.first == savedModel }) add(savedModel to savedModel)
                }
                modelOptions.forEach { (modelId, modelLabel) ->
                    val isSelected = savedModel == modelId
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isSelected) Color(0xFF007AFF)
                                else Color.White.copy(alpha = 0.10f)
                            )
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                SettingsStore.setLyricsTranslationModel(context, modelId)
                            }
                            .padding(horizontal = 13.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = modelLabel,
                            fontFamily = SFProDisplay,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.82f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Custom Prompt Field (shown if mode is Custom or can add custom nuances)
            if (settingsState.lyricsTranslateMode == "Custom") {
                Text(
                    text = "CUSTOM INSTRUCTIONS",
                    fontFamily = SFProDisplay,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.6f),
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    if (customPromptText.isEmpty()) {
                        Text(
                            text = "e.g. translate into poetic hinglish with rhymes",
                            fontFamily = SFProDisplay,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.35f)
                        )
                    }
                    BasicTextField(
                        value = customPromptText,
                        onValueChange = {
                            customPromptText = it
                            SettingsStore.setLyricsTranslationCustomPrompt(context, it)
                        },
                        textStyle = TextStyle(
                            fontFamily = SFProDisplay,
                            fontSize = 13.sp,
                            color = Color.White
                        ),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            // "Translate actual lines" Toggle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Translate actual lines",
                            fontFamily = SFProDisplay,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (settingsState.lyricsReplaceOriginal)
                                "Replaces original lines with translation"
                            else
                                "Displays translated subtitles below each line",
                            fontFamily = SFProDisplay,
                            fontSize = 11.5.sp,
                            color = AppleTextSecondary
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    AppleGlassSwitch(
                        checked = settingsState.lyricsReplaceOriginal,
                        onCheckedChange = { checked ->
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            SettingsStore.setLyricsReplaceOriginal(context, checked)
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Missing API Key Warning
            if (!hasApiKey) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFF9F0A).copy(alpha = 0.18f))
                        .border(0.75.dp, Color(0xFFFF9F0A).copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = Color(0xFFFF9F0A),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "API Key not configured for ${settingsState.lyricsTranslationProvider}. Please set it in Settings > Lyrics.",
                            fontFamily = SFProDisplay,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.95f),
                            lineHeight = 16.sp
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Live Translation Status & Feedback
            when (val status = translationStatus) {
                is LyricsTranslationHelper.TranslationStatus.Translating -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = AppleMusicRed
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Translating lyrics...",
                                    fontFamily = SFProDisplay,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                val lastLog = status.logs.lastOrNull() ?: "Sending request..."
                                Text(
                                    text = lastLog,
                                    fontFamily = SFProDisplay,
                                    fontSize = 11.5.sp,
                                    color = AppleTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        LyricsTranslationHelper.cancelTranslation()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Cancel",
                                    fontFamily = SFProDisplay,
                                    fontSize = 12.sp,
                                    color = Color(0xFFFF453A)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                is LyricsTranslationHelper.TranslationStatus.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFF453A).copy(alpha = 0.18f))
                            .border(0.75.dp, Color(0xFFFF453A).copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                tint = Color(0xFFFF453A),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = status.message,
                                fontFamily = SFProDisplay,
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                is LyricsTranslationHelper.TranslationStatus.Success -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF34C759).copy(alpha = 0.20f))
                            .border(0.75.dp, Color(0xFF34C759).copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = Color(0xFF34C759),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Lyrics translated successfully!",
                                fontFamily = SFProDisplay,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                else -> {}
            }

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isTranslatedForSong) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                LyricsTranslationHelper.clearSongTranslation(context, track.id)
                                Toast.makeText(context, "Translation cleared", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Clear",
                            fontFamily = SFProDisplay,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFF453A)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(if (isTranslatedForSong) 1.5f else 1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (isTranslating) Color.White.copy(alpha = 0.2f) else AppleMusicRed)
                        .clickable(enabled = !isTranslating) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            if (!hasApiKey) {
                                Toast.makeText(
                                    context,
                                    "Please set your API Key in Settings > Lyrics first",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@clickable
                            }
                            val lines = rawLyricsLines
                            if (lines.isEmpty()) {
                                Toast.makeText(context, "No lyrics found for this song", Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            LyricsTranslationHelper.translateSong(
                                context = context,
                                songId = track.id,
                                lines = lines,
                                targetLanguageCode = settingsState.lyricsTranslateLanguage,
                                mode = settingsState.lyricsTranslateMode,
                                provider = settingsState.lyricsTranslationProvider,
                                apiKey = settingsState.lyricsTranslationApiKey,
                                baseUrl = settingsState.lyricsTranslationBaseUrl,
                                model = settingsState.lyricsTranslationModel,
                                customPrompt = customPromptText.takeIf { it.isNotBlank() },
                                scope = scope
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isTranslating) "Translating..." else if (isTranslatedForSong) "Retranslate" else "Translate",
                        fontFamily = SFProDisplay,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }

    if (showLanguageDialog) {
        AiLanguageSelectorDialog(
            currentLanguageCode = settingsState.lyricsTranslateLanguage,
            onSelect = { code ->
                SettingsStore.setLyricsTranslateLanguage(context, code)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
}

@Composable
internal fun AiLanguageSelectorDialog(
    currentLanguageCode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val allLanguages = remember { LyricsTranslationHelper.AiTranslationLanguages.toList() }
    val filteredLanguages = remember(searchQuery) {
        if (searchQuery.isBlank()) allLanguages
        else allLanguages.filter { (code, name) ->
            name.contains(searchQuery, ignoreCase = true) || code.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1C1C1E))
                .border(0.75.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Language",
                        fontFamily = SFProDisplay,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable(onClick = onDismiss)
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Search field
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search languages...",
                            fontFamily = SFProDisplay,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = SFProDisplay,
                            fontSize = 13.sp,
                            color = Color.White
                        ),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Language list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    filteredLanguages.forEach { (code, name) ->
                        val isSelected = code == currentLanguageCode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) AppleMusicRed.copy(alpha = 0.25f)
                                    else Color.Transparent
                                )
                                .clickable { onSelect(code) }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                fontFamily = SFProDisplay,
                                fontSize = 14.5.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.88f),
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = AppleMusicRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
