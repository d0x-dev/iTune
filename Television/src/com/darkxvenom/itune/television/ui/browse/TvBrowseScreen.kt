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

package com.darkxvenom.itune.television.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.darkxvenom.itune.backend.MusicPlaylistRef
import com.darkxvenom.itune.backend.NewReleaseAlbum
import com.darkxvenom.itune.data.library.BrowseMoodsRepository
import com.darkxvenom.itune.data.library.ContentLocaleCatalog
import com.darkxvenom.itune.data.library.NewReleasesRepository
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.data.model.BrowseCategoryItem
import com.darkxvenom.itune.television.focus.SFProDisplay
import com.darkxvenom.itune.television.focus.TvAppleSilverBg
import com.darkxvenom.itune.television.focus.TvCard
import com.darkxvenom.itune.television.ui.common.TvAmbientBackground
import com.darkxvenom.itune.ui.artist.components.ExplicitBadge
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleOledBlack

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

/**
 * 10-Foot Television Browse Screen:
 * - Powered directly by [NewReleasesRepository] and [BrowseMoodsRepository].
 * - New Releases (regional albums & singles).
 * - Featured Playlists (editorial selections).
 * - Moods & Activities catalog.
 * - Music by Genre.
 */
@Composable
fun TvBrowseScreen(
    onCategoryClick: (BrowseCategoryItem) -> Unit = {},
    onReleaseClick: (browseId: String?, playlistId: String?, title: String, subtitle: String?, thumb: String?) -> Unit = { _, _, _, _, _ -> },
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BackHandler { onBack() }

    val settingsState by SettingsStore.state.collectAsStateWithLifecycle()
    val releases by NewReleasesRepository.releases.collectAsStateWithLifecycle()
    val featuredPlaylists by NewReleasesRepository.featuredPlaylists.collectAsStateWithLifecycle()
    val releasesLoading by NewReleasesRepository.isLoading.collectAsStateWithLifecycle()
    val categories by BrowseMoodsRepository.categories.collectAsStateWithLifecycle()
    val genres by BrowseMoodsRepository.genres.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        NewReleasesRepository.ensureLoaded()
        BrowseMoodsRepository.ensureLoaded()
    }

    val visibleReleases = remember(releases, settingsState.hideExplicitContent) {
        ContentLocaleCatalog.filterVisible(releases, settingsState.hideExplicitContent) { it.isExplicit }
    }
    val visiblePlaylists = remember(featuredPlaylists, settingsState.hideExplicitContent) {
        ContentLocaleCatalog.filterVisible(featuredPlaylists, settingsState.hideExplicitContent) { it.isExplicit }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvAppleSilverBg),
    ) {
        TvAmbientBackground(Modifier.fillMaxSize(), dim = 0.40f)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 64.dp),
        ) {
            // ── Section 1: New Releases Shelf ────────────────────────────────
            if (visibleReleases.isNotEmpty()) {
                item(key = "browse_new_releases_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "New Releases",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.Bold,
                        )

                        if (releasesLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                item(key = "browse_new_releases_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(visibleReleases.take(16), key = { it.browseId }) { release ->
                            TvBrowseReleaseCard(
                                release = release,
                                onClick = {
                                    onReleaseClick(
                                        release.browseId,
                                        null,
                                        release.title,
                                        release.artist,
                                        release.thumbnailUrl,
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // ── Section 2: Featured Playlists Shelf ──────────────────────────
            if (visiblePlaylists.isNotEmpty()) {
                item(key = "browse_featured_header") {
                    Spacer(Modifier.height(28.dp))
                    Text(
                        text = "Featured Playlists",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
                    )
                }

                item(key = "browse_featured_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(visiblePlaylists.take(16), key = { it.id }) { playlist ->
                            TvBrowsePlaylistCard(
                                playlist = playlist,
                                onClick = {
                                    onReleaseClick(
                                        null,
                                        playlist.id,
                                        playlist.title,
                                        playlist.author ?: "Apple Music",
                                        playlist.thumbnailUrl,
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // ── Section 3: Moods & Activities ────────────────────────────────
            if (categories.isNotEmpty()) {
                item(key = "browse_moods_header") {
                    Spacer(Modifier.height(28.dp))
                    Text(
                        text = "Moods & Activities",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
                    )
                }

                item(key = "browse_moods_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(categories, key = { it.id }) { cat ->
                            TvBrowseCategoryTile(
                                category = cat,
                                onClick = { onCategoryClick(cat) },
                            )
                        }
                    }
                }
            }

            // ── Section 4: Music by Genre ────────────────────────────────────
            if (genres.isNotEmpty()) {
                item(key = "browse_genres_header") {
                    Spacer(Modifier.height(28.dp))
                    Text(
                        text = "Music by Genre",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
                    )
                }

                item(key = "browse_genres_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(genres, key = { it.id }) { genre ->
                            TvBrowseCategoryTile(
                                category = genre,
                                onClick = { onCategoryClick(genre) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * High-res New Release Card with 18.dp rounded corners.
 */
@Composable
private fun TvBrowseReleaseCard(
    release: NewReleaseAlbum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvCard(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        scaleOnFocus = 1.05f,
        modifier = modifier.width(185.dp),
    ) { isFocused ->
        Column(modifier = Modifier.width(185.dp)) {
            Box(
                modifier = Modifier
                    .size(185.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF222226)),
            ) {
                AsyncImage(
                    model = release.thumbnailUrl,
                    contentDescription = release.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                if (isFocused) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0xDD000000)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Open",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = release.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (release.isExplicit) {
                    Spacer(Modifier.width(4.dp))
                    ExplicitBadge(color = Color.White.copy(alpha = 0.85f))
                }
            }

            Text(
                text = release.artist,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Editorial Playlist Card with 18.dp rounded corners.
 */
@Composable
private fun TvBrowsePlaylistCard(
    playlist: MusicPlaylistRef,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvCard(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        scaleOnFocus = 1.05f,
        modifier = modifier.width(185.dp),
    ) { isFocused ->
        Column(modifier = Modifier.width(185.dp)) {
            Box(
                modifier = Modifier
                    .size(185.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF222226)),
            ) {
                AsyncImage(
                    model = playlist.thumbnailUrl,
                    contentDescription = playlist.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                if (isFocused) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0xDD000000)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Open",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = playlist.title,
                color = Color.White,
                fontSize = 15.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = playlist.author ?: "Apple Music",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Sleek category / mood tile with vibrant background.
 */
@Composable
private fun TvBrowseCategoryTile(
    category: BrowseCategoryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        scaleOnFocus = 1.06f,
        modifier = modifier
            .width(220.dp)
            .height(115.dp),
    ) { isFocused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF242C3E),
                            Color(0xFF161B26),
                        ),
                    ),
                ),
        ) {
            if (category.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = category.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.75f),
                                ),
                            ),
                        ),
                )
            }

            Text(
                text = category.title,
                color = Color.White,
                fontSize = 17.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            )
        }
    }
}
