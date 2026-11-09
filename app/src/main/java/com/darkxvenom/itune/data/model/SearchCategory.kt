package com.darkxvenom.itune.data.model

import androidx.annotation.DrawableRes
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
    CHILL
}

data class SearchCategory(
    val id: String,
    val title: String,
    val type: SearchCategoryType,
    val backgroundColor: Color,
    @DrawableRes val imageRes: Int? = null
)
