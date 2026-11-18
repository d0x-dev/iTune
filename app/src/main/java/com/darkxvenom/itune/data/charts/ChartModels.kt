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

package com.darkxvenom.itune.data.charts

import androidx.compose.runtime.Immutable

@Immutable
data class ChartTrack(
    val rank: Int,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val appleMusicUrl: String? = null,
    val id: String? = null,
    val durationSec: Long = 0,
    val isExplicit: Boolean = false,
    val streamUrl: String? = null,
)

@Immutable
data class ChartArtist(
    val rank: Int,
    val name: String,
    val thumbnailUrl: String?,
    val id: String? = null,
)

@Immutable
data class ChartAlbum(
    val rank: Int,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val appleMusicUrl: String? = null,
    val id: String? = null,
)

enum class ChartPlatform(val title: String) {
    APPLE("Apple Music"),
    ITUNE("iTune"),
    YOUTUBE("YouTube"),
    JIOSAAVN("JioSaavn"),
}
// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

val ChartRegionSlugToName: Map<String, String> = mapOf(
    "system" to "System Default",
    "us" to "United States",
    "in" to "India",
    "gb" to "United Kingdom",
    "ca" to "Canada",
    "au" to "Australia",
    "jp" to "Japan",
    "kr" to "South Korea",
    "de" to "Germany",
    "fr" to "France",
    "br" to "Brazil",
    "mx" to "Mexico",
    "it" to "Italy",
    "es" to "Spain",
    "nl" to "Netherlands",
    "se" to "Sweden",
    "no" to "Norway",
    "dk" to "Denmark",
    "fi" to "Finland",
    "pl" to "Poland",
    "tr" to "Turkey",
    "za" to "South Africa",
    "ng" to "Nigeria",
    "id" to "Indonesia",
    "my" to "Malaysia",
    "ph" to "Philippines",
    "th" to "Thailand",
    "vn" to "Vietnam",
    "tw" to "Taiwan",
    "hk" to "Hong Kong",
    "sg" to "Singapore",
    "ar" to "Argentina",
    "co" to "Colombia",
    "cl" to "Chile",
    "pe" to "Peru",
    "eg" to "Egypt",
    "sa" to "Saudi Arabia",
    "ae" to "United Arab Emirates",
    "il" to "Israel"
)
