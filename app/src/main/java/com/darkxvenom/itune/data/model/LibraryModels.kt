package com.darkxvenom.itune.data.model

import androidx.annotation.DrawableRes

data class LibraryPinItem(
    val id: String,
    val title: String,
    val imageUrl: String
)

data class LibraryMenuItem(
    val id: String,
    val title: String,
    @param:DrawableRes val iconRes: Int
)

data class LibraryRecentAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val imageUrl: String
)
