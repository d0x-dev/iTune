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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkxvenom.itune.television.focus.TvCard
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.television.focus.SFProDisplay

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

data class TvMoodCategory(
    val title: String,
    val subtitle: String,
    val colors: List<Color>,
)

val TV_MOOD_CATEGORIES = listOf(
    TvMoodCategory("Chill & Relax", "Unwind with mellow tunes", listOf(Color(0xFF1E3C72), Color(0xFF2A5298))),
    TvMoodCategory("Workout & Energy", "Power through high BPM beats", listOf(Color(0xFFFF416C), Color(0xFFFF4B2B))),
    TvMoodCategory("Deep Focus", "Ambient & study instrumentals", listOf(Color(0xFF11998E), Color(0xFF38EF7D))),
    TvMoodCategory("Party & Dance", "Upbeat anthems and club bangers", listOf(Color(0xFF8A2387), Color(0xFFE94057))),
    TvMoodCategory("Pop Hits", "Global top chart smashes", listOf(Color(0xFFF7971E), Color(0xFFFFD200))),
    TvMoodCategory("Hip-Hop & R&B", "Heavy 808s and smooth soul", listOf(Color(0xFF4A00E0), Color(0xFF8E2DE2))),
    TvMoodCategory("Rock & Alt", "Guitars, raw energy and anthems", listOf(Color(0xFFCB356B), Color(0xFFBD3F32))),
    TvMoodCategory("Sleep & Peace", "Slow drift into quiet dreams", listOf(Color(0xFF0F2027), Color(0xFF203A43))),
)

@Composable
fun TvBrowseScreen(
    onCategoryClick: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppleOledBlack)
            .padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        Text(
            text = "Browse & Moods",
            color = Color.White,
            fontSize = 28.sp,
            fontFamily = SFProDisplay,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(22.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(TV_MOOD_CATEGORIES, key = { it.title }) { cat ->
                TvCard(
                    onClick = { onCategoryClick(cat.title) },
                    shape = RoundedCornerShape(18.dp),
                    scaleOnFocus = 1.04f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(cat.colors))
                            .padding(20.dp),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        Column {
                            Text(
                                text = cat.title,
                                color = Color.White,
                                fontSize = 19.sp,
                                fontFamily = SFProDisplay,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = cat.subtitle,
                                color = Color.White.copy(alpha = 0.80f),
                                fontSize = 12.5.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
