package com.darkxvenom.itune.data.model

data class BrowseCategoryItem(
    val id: String,
    val title: String,
    val imageUrl: String
)

enum class BrowseSegment(val title: String) {
    CATALOG("Catalog"),
    PLAYLISTS("Playlists"),
    INSTRUMENTS("Instruments")
}
