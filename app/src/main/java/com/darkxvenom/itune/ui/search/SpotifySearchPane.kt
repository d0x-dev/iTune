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

package com.darkxvenom.itune.ui.search

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.data.model.BrowseType
import com.darkxvenom.itune.data.model.PendingArtistNav
import com.darkxvenom.itune.data.model.Song
import com.darkxvenom.itune.data.spotify.SpotifyEntity
import com.darkxvenom.itune.data.spotify.SpotifyHomeRepository
import com.darkxvenom.itune.data.spotify.SpotifySearch
import com.darkxvenom.itune.data.spotify.SpotifySearchResults
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private enum class SpFilter(val label: String) { ALL("All"), SONGS("Songs"), ARTISTS("Artists"), ALBUMS("Albums"), PLAYLISTS("Playlists"), PODCASTS("Podcasts") }

/** Source switch shown above recent searches: YouTube Music (default) / Spotify. */
@Composable
fun SearchSourceSwitch(useSpotify: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp).height(36.dp)
            .clip(RoundedCornerShape(9.dp)).background(Color(0xFF1C1C1E)).padding(2.5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(false to "YouTube Music", true to "Spotify").forEach { (isSp, label) ->
            val sel = useSpotify == isSp
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(7.dp))
                    .background(if (sel) (if (isSp) Color(0xFF1DB954) else Color(0xFFFA2D48)) else Color.Transparent)
                    .clickable { onChange(isSp) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = Color.White, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}

@Composable
fun SpotifySearchPane(query: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(SpFilter.ALL) }
    val state = produceState<Result<SpotifySearchResults>?>(null, query) {
        value = null
        value = runCatching { SpotifySearch.search(query) }
    }.value

    fun playSong(list: List<Song>, index: Int) {
        scope.launch {
            val tracks = coroutineScope {
                list.drop(index).take(20).map { async { SpotifyHomeRepository.matchSong(it) } }
            }.mapNotNull { it.await() }
            if (tracks.isEmpty()) Toast.makeText(context, "Couldn't find this song on YouTube Music", Toast.LENGTH_SHORT).show()
            else {
                SpotifyHomeRepository.markOrigin(tracks.map { it.id })
                MusicPlayerState.play(tracks.first(), tracks)
            }
        }
    }
    fun open(e: SpotifyEntity) {
        val type = when {
            e.browseId.startsWith("spotify:artist:") -> BrowseType.ARTIST
            e.browseId.startsWith("spotify:album:") -> BrowseType.ALBUM
            else -> BrowseType.PLAYLIST
        }
        MusicPlayerState.pendingArtistNav = PendingArtistNav(e.browseId, e.title, e.subtitle, e.image, type)
    }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpFilter.values().forEach { f ->
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (filter == f) Color(0xFF1DB954) else Color(0xFF2C2C2E))
                        .clickable { filter = f }.padding(horizontal = 14.dp, vertical = 7.dp),
                ) { Text(f.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
            }
        }
        when {
            state == null -> Text("Searching Spotify…", color = Color.Gray, modifier = Modifier.padding(16.dp))
            state.isFailure -> Text("Spotify search failed. Try again.", color = Color.Gray, modifier = Modifier.padding(16.dp))
            else -> {
                val r = state.getOrThrow()
                if (r.isEmpty) Text("No results on Spotify", color = Color.Gray, modifier = Modifier.padding(16.dp))
                val all = filter == SpFilter.ALL
                if (r.songs.isNotEmpty() && (all || filter == SpFilter.SONGS)) {
                    SectionHeader("Songs", all) { filter = SpFilter.SONGS }
                    (if (all) r.songs.take(4) else r.songs).forEachIndexed { i, s ->
                        Row(Modifier.fillMaxWidth().clickable { playSong(r.songs, i) }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Art(s.thumbnailUrl, false)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(s.title, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(s.artist, color = Color.Gray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                entitySection("Artists", r.artists, all, filter == SpFilter.ARTISTS, true, { filter = SpFilter.ARTISTS }, ::open)
                entitySection("Albums", r.albums, all, filter == SpFilter.ALBUMS, false, { filter = SpFilter.ALBUMS }, ::open)
                entitySection("Playlists", r.playlists, all, filter == SpFilter.PLAYLISTS, false, { filter = SpFilter.PLAYLISTS }, ::open)
                entitySection("Podcasts", r.shows, all, filter == SpFilter.PODCASTS, false, { filter = SpFilter.PODCASTS }, ::open)
            }
        }
    }
}

@Composable
private fun entitySection(
    title: String, items: List<SpotifyEntity>, all: Boolean, selected: Boolean, round: Boolean,
    onMore: () -> Unit, onOpen: (SpotifyEntity) -> Unit,
) {
    if (items.isEmpty() || !(all || selected)) return
    SectionHeader(title, all, onMore)
    (if (all) items.take(3) else items).forEach { e ->
        Row(Modifier.fillMaxWidth().clickable { onOpen(e) }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Art(e.image, round)
            Column(Modifier.padding(start = 12.dp)) {
                Text(e.title, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(e.subtitle, color = Color.Gray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, showMore: Boolean, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (showMore) Text("More", color = Color(0xFF1DB954), fontSize = 14.sp, modifier = Modifier.clickable(onClick = onMore))
    }
}

@Composable
private fun Art(url: String?, round: Boolean) {
    AsyncImage(
        model = url, contentDescription = null, contentScale = ContentScale.Crop,
        modifier = Modifier.size(52.dp).clip(if (round) CircleShape else RoundedCornerShape(6.dp)).background(Color(0xFF2C2C2E)),
    )
}
// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.
