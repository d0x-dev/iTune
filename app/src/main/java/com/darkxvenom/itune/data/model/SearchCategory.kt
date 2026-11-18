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

package com.darkxvenom.itune.data.model

import androidx.compose.ui.graphics.Color

enum class SearchCategoryType {
    APPLE_MUSIC_LIVE,
    HITS,
    ALTERNATIVE,
    POP,
    HIP_HOP,
    COUNTRY,
    DANCE,
    ROCK,
    LATIN,
    RNB,
    CLASSICAL,
    CHILL,
    COMMUNITY,
    ENERGIZE,
    FEEL_GOOD,
    FOCUS,
    GAMING,
    PARTY,
    ROMANCE,
    SAD,
    SLEEP,
    WORKOUT,
    AFRICAN,
    ARABIC,
    BENGALI,
    BOLLYWOOD
}
// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

data class SearchCategory(
    val id: String,
    val title: String,
    val type: SearchCategoryType,
    val backgroundColor: Color,
    val imageUrl: String? = null
)
