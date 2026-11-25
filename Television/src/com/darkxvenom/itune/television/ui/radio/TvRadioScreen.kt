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

package com.darkxvenom.itune.television.ui.radio


import androidx.compose.ui.res.stringResource
import com.darkxvenom.itune.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.darkxvenom.itune.radio.OnlineRadioController
import com.darkxvenom.itune.radio.OnlineRadioStation
import com.darkxvenom.itune.radio.RadioCountryOption
import com.darkxvenom.itune.television.focus.SFProDisplay
import com.darkxvenom.itune.television.focus.TvAppleSilverBg
import com.darkxvenom.itune.television.focus.TvCard
import com.darkxvenom.itune.television.focus.tvFocusable
import com.darkxvenom.itune.television.ui.common.TvAmbientBackground
import com.darkxvenom.itune.ui.components.AnimatedEqualizer
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleOledBlack

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

/**
 * 10-Foot Television Live Radio Screen:
 * - Powered directly by backend [OnlineRadioController].
 * - Hero Live Broadcast Banner with instant 1-touch play/pause.
 * - Country & Region filters.
 * - Live Station Cards with animated playback equalizers and bitrate badges.
 * - Favorites & Global Popular FM radio stations.
 */
@Composable
fun TvRadioScreen(
    onStationClick: (OnlineRadioStation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        OnlineRadioController.ensureInit(context)
        OnlineRadioController.ensureCatalogLoaded()
    }

    val radioState by OnlineRadioController.state.collectAsStateWithLifecycle()
    val stations = radioState.stations
    val favorites = radioState.favoriteStations
    val countries = radioState.countries
    val selectedCountry = radioState.selectedCountry
    val currentStation = radioState.currentStation
    val isPlaying = radioState.isPlaying
    val isLoading = radioState.loadingStations

    val heroStation = currentStation ?: stations.firstOrNull()

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
            // ── Hero Live Broadcast Card ─────────────────────────────────────
            if (heroStation != null) {
                item(key = "radio_hero") {
                    TvRadioHeroBanner(
                        station = heroStation,
                        isPlaying = isPlaying && currentStation?.uuid == heroStation.uuid,
                        onPlayClick = {
                            if (currentStation?.uuid == heroStation.uuid) {
                                OnlineRadioController.togglePlayPause()
                            } else {
                                onStationClick(heroStation)
                            }
                        },
                    )
                }
            }

            // ── Favorites Shelf (if any) ─────────────────────────────────────
            if (favorites.isNotEmpty()) {
                item(key = "radio_favs_header") {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.ui_favorite_stations),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
                    )
                }

                item(key = "radio_favs_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(favorites, key = { "fav_${it.uuid}" }) { station ->
                            val isStationActive = currentStation?.uuid == station.uuid
                            TvRadioStationCard(
                                station = station,
                                isPlaying = isPlaying && isStationActive,
                                isCurrent = isStationActive,
                                onClick = {
                                    if (isStationActive) {
                                        OnlineRadioController.togglePlayPause()
                                    } else {
                                        onStationClick(station)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // ── Country Selector Filter Pills ────────────────────────────────
            if (countries.isNotEmpty()) {
                item(key = "radio_country_header") {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.ui_browse_by_country),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
                    )
                }

                item(key = "radio_country_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(countries.take(20), key = { it.code }) { country ->
                            val isSelected = selectedCountry?.code == country.code
                            TvCountryPill(
                                country = country,
                                isSelected = isSelected,
                                onClick = { OnlineRadioController.selectCountry(country) },
                            )
                        }
                    }
                }
            }

            // ── Live Radio Stations Grid / Shelf ─────────────────────────────
            item(key = "radio_stations_header") {
                Spacer(Modifier.height(28.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (selectedCountry != null) "Stations in ${selectedCountry.name}" else "Popular Live Radio",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                    )

                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Group stations in rows of horizontal carousels
            val stationChunks = stations.chunked(12)
            itemsIndexed(stationChunks, key = { index, _ -> "radio_chunk_$index" }) { _, rowStations ->
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(bottom = 20.dp),
                ) {
                    items(rowStations, key = { it.uuid }) { station ->
                        val isStationActive = currentStation?.uuid == station.uuid
                        TvRadioStationCard(
                            station = station,
                            isPlaying = isPlaying && isStationActive,
                            isCurrent = isStationActive,
                            onClick = {
                                if (isStationActive) {
                                    OnlineRadioController.togglePlayPause()
                                } else {
                                    onStationClick(station)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Top Apple Music Radio style broadcast banner.
 */
@Composable
private fun TvRadioHeroBanner(
    station: OnlineRadioStation,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvCard(
        onClick = onPlayClick,
        shape = RoundedCornerShape(20.dp),
        scaleOnFocus = 1.02f,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 8.dp)
            .height(220.dp),
    ) { isFocused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF1E2838),
                            Color(0xFF141923),
                            Color(0xFF0F1218),
                        ),
                    ),
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Station Icon / Art
                Box(
                    modifier = Modifier
                        .size(164.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF2C3240)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!station.faviconUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = station.faviconUrl,
                            contentDescription = station.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Radio,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.60f),
                            modifier = Modifier.size(64.dp),
                        )
                    }
                }

                Spacer(Modifier.width(28.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AppleMusicRed)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.ui_live),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = SFProDisplay,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                            )
                        }

                        if (isPlaying) {
                            AnimatedEqualizer(
                                isPlaying = true,
                                color = Color.White,
                                size = 16.dp,
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = station.name,
                        color = Color.White,
                        fontSize = 30.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(4.dp))

                    val location = station.locationLabel.ifBlank { station.countryName }
                    val tags = station.primaryTagSummary
                    Text(
                        text = if (location.isNotBlank()) "$location • $tags" else tags,
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = 14.sp,
                        fontFamily = SFProDisplay,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(16.dp))

                    // Play / Pause Action Pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isFocused) Color.White else Color(0x33FFFFFF))
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Listen Live",
                            tint = if (isFocused) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (isPlaying) "Pause Radio" else "Listen Live",
                            color = if (isFocused) Color.Black else Color.White,
                            fontSize = 14.sp,
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Filter pill for Countries.
 */
@Composable
private fun TvCountryPill(
    country: RadioCountryOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .tvFocusable(
                shape = RoundedCornerShape(10.dp),
                scaleOnFocus = 1.08f,
                interactionSource = interaction,
                onClick = onClick,
            )
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isFocused -> Color.White
                    isSelected -> AppleMusicRed
                    else -> Color(0x22FFFFFF)
                },
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = country.name,
            color = if (isFocused) Color.Black else Color.White,
            fontSize = 13.5.sp,
            fontFamily = SFProDisplay,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/**
 * Live Radio Station Card.
 */
@Composable
private fun TvRadioStationCard(
    station: OnlineRadioStation,
    isPlaying: Boolean,
    isCurrent: Boolean,
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
                    .background(Color(0xFF1E1E24))
                    .then(
                        if (isCurrent) Modifier.border(2.dp, AppleMusicRed, RoundedCornerShape(18.dp))
                        else Modifier
                    ),
            ) {
                if (!station.faviconUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = station.faviconUrl,
                        contentDescription = station.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0xFF2C2C34), Color(0xFF16161A))
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Radio,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(54.dp),
                        )
                    }
                }

                // Bitrate Badge
                if (station.bitrateKbps > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.ui_k, station.bitrateKbps),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Live Equalizer overlay if playing
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.40f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedEqualizer(
                            isPlaying = true,
                            color = AppleMusicRed,
                            size = 36.dp,
                        )
                    }
                } else if (isFocused) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(AppleMusicRed),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.play),
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = station.name,
                color = Color.White,
                fontSize = 15.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = station.primaryTagSummary.ifBlank { station.countryName },
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
