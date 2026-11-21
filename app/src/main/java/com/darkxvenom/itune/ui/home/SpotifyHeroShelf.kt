/*
 * Copyright (C) 2026 DxV Studio ~~ Dark
 *
 * Originally created by DxV Studio ~~ Dark.
 * Credits: DxV Studio, AirBeats, and Dark.
 *
 * This file is part of iTune and is licensed under the GNU General Public License v3.0 (GPL-3.0).
 * Any copying, modification, or redistribution of this code must strictly comply with the
 * terms of the project LICENSE file and retain full author credit.
 */

package com.darkxvenom.itune.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.draw.shadow
import com.darkxvenom.itune.R
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.darkxvenom.itune.data.model.HomeFeedItem
import com.darkxvenom.itune.data.model.HomeFeedShelf
import com.darkxvenom.itune.data.spotify.HeroCardData
import com.darkxvenom.itune.data.spotify.SpotifyHeroLoader
import com.darkxvenom.itune.ui.artist.canvas.CanvasArtworkPlayer

private val CardShape = RoundedCornerShape(10.dp)

/**
 * Every mostly-visible big card plays its Canvas, but at most [MAX] at once (each is a full
 * ExoPlayer + decoder), started one after another and paused while the row scrolls, so it stays smooth.
 */
private object HeroPlayback {
    const val MAX = 4
    val ids = mutableStateListOf<String>()
    fun add(id: String) { if (id !in ids) { if (ids.size >= MAX) ids.removeAt(0); ids.add(id) } }
    fun remove(id: String) { ids.remove(id) }
}

/**
 * Spotify "Made for you" style big cards.
 *
 * - The cover is shown immediately; the Canvas of the playlist's first song loads in the
 *   background and then plays across the whole card.
 * - The round arrow steps to the Canvas of the 2nd and then the 3rd song.
 * - The tagline is the playlist's own Spotify description.
 * - Play starts the playlist, Preview (or the card) opens the playlist screen.
 */
@Composable
fun SpotifyHeroShelf(
    shelf: HomeFeedShelf,
    onItemClick: (HomeFeedItem, List<HomeFeedItem>) -> Unit,
    onItemPlay: (HomeFeedItem) -> Unit,
) {
    Column {
        Text(
            shelf.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val rowState = rememberLazyListState()
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(shelf.items, key = { it.browseId ?: it.title }) { item ->
                SpotifyHeroCard(
                    item = item,
                    scrolling = rowState.isScrollInProgress,
                    onOpen = { onItemClick(item, shelf.items) },
                    onPlay = { onItemPlay(item) },
                )
            }
        }
    }
}

@Composable
private fun SpotifyHeroCard(
    item: HomeFeedItem,
    scrolling: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    val playlistId = item.browseId?.substringAfterLast(':')
    var data by remember(playlistId) {
        mutableStateOf(playlistId?.let { SpotifyHeroLoader.cached(it) } ?: HeroCardData())
    }
    var slideIndex by remember(playlistId) { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    // Controls appear on tap and hide again 5s after the last interaction.
    var controlsVisible by remember { mutableStateOf(false) }
    var touch by remember { mutableIntStateOf(0) }
    LaunchedEffect(controlsVisible, touch, menuOpen) {
        if (controlsVisible && !menuOpen) {
            delay(5_000)
            controlsVisible = false
        }
    }
    val poke: () -> Unit = { controlsVisible = true; touch++ }

    LaunchedEffect(playlistId) {
        if (playlistId != null) SpotifyHeroLoader.load(playlistId) { data = it }
    }

    val cardId = item.browseId ?: item.title
    val screenH = LocalConfiguration.current.screenHeightDp.toFloat() * LocalDensity.current.density
    val screenW = LocalConfiguration.current.screenWidthDp.toFloat() * LocalDensity.current.density
    DisposableEffect(cardId) { onDispose { HeroPlayback.remove(cardId) } }
    val inSlot = cardId in HeroPlayback.ids
    // Stagger decoder start-up so several cards never initialise at the same instant.
    var startReady by remember { mutableStateOf(false) }
    LaunchedEffect(inSlot) {
        if (inSlot) {
            delay(250L + 300L * HeroPlayback.ids.indexOf(cardId).coerceAtLeast(0))
            startReady = true
        } else startReady = false
    }
    val canPlay = inSlot && startReady

    val slides = data.slides
    val slide = slides.getOrNull(slideIndex.coerceIn(0, (slides.size - 1).coerceAtLeast(0)))

    Box(
        Modifier
            .width(320.dp)
            .height(500.dp)
            .clip(CardShape)
            .background(Color(0xFF181818))
            .border(0.5.dp, Color(0x22FFFFFF), CardShape)
            .onGloballyPositioned { c ->
                val b = c.boundsInWindow()
                val w = (minOf(b.right, screenW) - maxOf(b.left, 0f)).coerceAtLeast(0f) / b.width.coerceAtLeast(1f)
                val h = (minOf(b.bottom, screenH) - maxOf(b.top, 0f)).coerceAtLeast(0f) / b.height.coerceAtLeast(1f)
                val shown = w * h
                if (shown > 0.5f) HeroPlayback.add(cardId) else if (shown < 0.25f) HeroPlayback.remove(cardId)
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { if (controlsVisible) controlsVisible = false else poke() },
    ) {
        // 1) Cover first, 2) Canvas of the current song on top once it is available.
        AsyncImage(item.thumbnailUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (slide != null && canPlay) {
            key(slide.canvas.url) {
                CanvasArtworkPlayer(
                    canvas = slide.canvas, isPlaying = true, modifier = Modifier.fillMaxSize(),
                    pausedForTransition = scrolling,
                )
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0xAA000000),
                    0.28f to Color.Transparent,
                    0.62f to Color.Transparent,
                    1f to Color(0xDD000000),
                ),
            ),
        )

        Column(Modifier.fillMaxSize().padding(14.dp)) {
            if (slides.size > 1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    slides.indices.forEach { i ->
                        Box(
                            Modifier.weight(1f).height(3.dp).clip(CircleShape)
                                .background(if (i == slideIndex) Color.White else Color(0x55FFFFFF)),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpen), verticalAlignment = Alignment.Top) {
                AsyncImage(
                    item.thumbnailUrl, null,
                    Modifier.size(64.dp).clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop,
                )
                Column(Modifier.padding(start = 12.dp)) {
                    Text(
                        item.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        lineHeight = 26.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text("Playlist \u2022 Spotify", color = Color(0xCCFFFFFF), fontSize = 13.sp)
                }
            }

            Spacer(Modifier.weight(1f))

            if (slide != null) {
                Text(
                    "\u266A ${slide.title} \u2022 ${slide.artist}",
                    color = Color(0xB3FFFFFF), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
            }
            data.tagline?.let {
                Text(
                    it, color = Color(0xE6FFFFFF), fontSize = 15.sp, lineHeight = 20.sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))

            AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(Color(0xCC1E2A2A))
                            .clickable(onClick = onOpen)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.ic_preview_wave), null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Preview", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.weight(1f))
                    Box {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).clickable { menuOpen = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.MoreHoriz, "More", tint = Color(0xCCFFFFFF), modifier = Modifier.size(24.dp))
                        }
                        if (menuOpen) {
                            GlassMenu(
                                playlistId = playlistId,
                                title = item.title,
                                onDismiss = { menuOpen = false },
                                onPlay = onPlay,
                                onOpen = onOpen,
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(Color.White).clickable(onClick = onPlay),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.PlayArrow, "Play ${item.title}", tint = Color.Black, modifier = Modifier.size(34.dp))
                    }
                }
            }
        }

        // Previous / next Canvas (song 1 → 2 → 3 → 1 …). Visible only while the controls are.
        if (slides.size > 1) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp),
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color(0xCC121212))
                        .clickable { slideIndex = (slideIndex - 1 + slides.size) % slides.size; touch++ },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous song preview", tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color(0xCC121212))
                        .clickable { slideIndex = (slideIndex + 1) % slides.size; touch++ },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next song preview", tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

/** Apple-style "liquid glass" action sheet shown from the card's "..." button. */
@Composable
private fun GlassMenu(
    playlistId: String?,
    title: String,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    val link = playlistId?.let { "https://open.spotify.com/playlist/$it" }
    val shape = RoundedCornerShape(24.dp)
    Popup(
        alignment = Alignment.BottomEnd,
        offset = IntOffset(0, -120),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .width(236.dp)
                .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        0f to Color(0xD9505057),
                        1f to Color(0xD9242428),
                    ),
                )
                .border(
                    1.dp,
                    Brush.linearGradient(
                        0f to Color(0x99FFFFFF),
                        0.5f to Color(0x1AFFFFFF),
                        1f to Color(0x66FFFFFF),
                    ),
                    shape,
                )
                .padding(vertical = 6.dp),
        ) {
            GlassItem(Icons.Rounded.PlayArrow, "Play") { onDismiss(); onPlay() }
            GlassDivider()
            GlassItem(Icons.Rounded.QueueMusic, "Open Playlist") { onDismiss(); onOpen() }
            GlassDivider()
            GlassItem(Icons.Rounded.Share, "Share") {
                onDismiss()
                link?.let {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "$title \u2022 $it")
                    }
                    context.startActivity(Intent.createChooser(send, "Share playlist").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            GlassDivider()
            GlassItem(Icons.Rounded.ContentCopy, "Copy Link") {
                onDismiss()
                link?.let {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("Playlist link", it))
                    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                }
            }
            GlassDivider()
            GlassItem(Icons.Rounded.OpenInNew, "Open in Spotify") {
                onDismiss()
                link?.let {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(it)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color(0x33FFFFFF))
}

@Composable
private fun GlassItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}
