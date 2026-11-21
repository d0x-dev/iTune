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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.darkxvenom.itune.data.artist.CanvasArtwork
import com.darkxvenom.itune.data.canvas.SpotifyCanvas
import com.darkxvenom.itune.data.model.HomeFeedItem
import com.darkxvenom.itune.data.model.HomeFeedShelf
import com.darkxvenom.itune.data.spotify.SpotifyLibrary
import com.darkxvenom.itune.ui.artist.canvas.CanvasArtworkPlayer
import java.util.concurrent.ConcurrentHashMap

private val heroCanvasCache = ConcurrentHashMap<String, CanvasArtwork>()

private suspend fun heroCanvas(item: HomeFeedItem): CanvasArtwork? {
    val id = item.browseId?.substringAfterLast(':') ?: return null
    heroCanvasCache[id]?.let { return it }
    val tracks = runCatching { SpotifyLibrary.fetchPlaylistDetails(id).second }.getOrNull().orEmpty()
    for (t in tracks.take(5)) {
        val c = runCatching { SpotifyCanvas.search(t.title, t.artist, t.album) }.getOrNull()
        if (c != null) { heroCanvasCache[id] = c; return c }
    }
    return null
}

/** Spotify "Made for you" style large cards: cover top-left, title, looping muted video behind. */
@Composable
fun SpotifyHeroShelf(shelf: HomeFeedShelf, onItemClick: (HomeFeedItem, List<HomeFeedItem>) -> Unit) {
    Column {
        Text(
            shelf.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            items(shelf.items, key = { it.browseId ?: it.title }) { item ->
                var canvas by remember(item.browseId) { mutableStateOf<CanvasArtwork?>(null) }
                LaunchedEffect(item.browseId) { canvas = heroCanvas(item) }
                Box(
                    Modifier.width(300.dp).height(380.dp).clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF181818)).clickable { onItemClick(item, shelf.items) },
                ) {
                    AsyncImage(item.thumbnailUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    canvas?.let { CanvasArtworkPlayer(canvas = it, isPlaying = true, modifier = Modifier.fillMaxSize()) }
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent, Color(0x66000000)))))
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        AsyncImage(item.thumbnailUrl, null, Modifier.size(72.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(item.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("Playlist • Spotify", color = Color(0xCCFFFFFF), fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.
