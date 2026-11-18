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

import androidx.compose.runtime.Immutable

@Immutable
data class BrowseCategoryItem(
    val id: String,
    val title: String,
    val imageUrl: String,
    val browseId: String = "FEmusic_moods_and_genres_category",
    val params: String? = null
)
// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

enum class BrowseSegment(val title: String) {
    CATALOG("Moods"),
    CHARTS("Charts"),
    INSTRUMENTS("Newly Added")
}
