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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.darkxvenom.itune.radio.OnlineRadioController
import com.darkxvenom.itune.radio.OnlineRadioStation
import com.darkxvenom.itune.television.focus.SFProDisplay
import com.darkxvenom.itune.television.focus.TvCard
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleOledBlack

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

@Composable
fun TvRadioScreen(
    onStationClick: (OnlineRadioStation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val radioState by OnlineRadioController.state.collectAsStateWithLifecycle()
    val stations = radioState.stations

    val topStations = stations.take(12)
    val remainingStations = stations.drop(12)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AppleOledBlack),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
    ) {
        item(key = "radio_featured_header") {
            Text(
                text = "Live Radio Stations",
                color = Color.White,
                fontSize = 26.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 12.dp),
            )
        }

        item(key = "radio_featured_row") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(topStations, key = { it.uuid }) { station ->
                    TvRadioStationCard(
                        station = station,
                        onClick = { onStationClick(station) },
                    )
                }
            }
        }

        if (remainingStations.isNotEmpty()) {
            item(key = "radio_more_header") {
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "Explore Stations by Genre",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 12.dp),
                )
            }

            item(key = "radio_more_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(remainingStations, key = { it.uuid }) { station ->
                        TvRadioStationCard(
                            station = station,
                            onClick = { onStationClick(station) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvRadioStationCard(
    station: OnlineRadioStation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvCard(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.width(185.dp),
    ) { isFocused ->
        Column(modifier = Modifier.width(185.dp)) {
            Box(
                modifier = Modifier
                    .size(185.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF1E1E22)),
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

                if (isFocused) {
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
                            contentDescription = "Play",
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
                text = station.primaryTagSummary,
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
