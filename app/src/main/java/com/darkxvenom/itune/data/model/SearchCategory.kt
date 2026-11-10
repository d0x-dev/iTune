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

data class SearchCategory(
    val id: String,
    val title: String,
    val type: SearchCategoryType,
    val backgroundColor: Color,
    val imageUrl: String? = null
)
