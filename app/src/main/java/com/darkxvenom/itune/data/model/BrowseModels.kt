package com.darkxvenom.itune.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class BrowseCategoryItem(
    val id: String,
    val title: String,
    val imageUrl: String,
    val browseId: String = "FEmusic_moods_and_genres_category",
    val params: String? = null
)

enum class BrowseSegment(val title: String) {
    CATALOG("Catalog"),
    PLAYLISTS("Playlists"),
    INSTRUMENTS("Instruments")
}
