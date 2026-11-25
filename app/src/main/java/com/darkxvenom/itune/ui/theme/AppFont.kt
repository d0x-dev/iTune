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

package com.darkxvenom.itune.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.darkxvenom.itune.R

enum class AppFont(val label: String, val key: String) {
    SYSTEM("System Default", "system"),
    SF_PRO("SF Pro Display", "sf_pro"),
    DOTO("Doto", "doto"),
    DOTO_ROUNDED("Doto Rounded", "doto_rounded"),
    DOTRICE("Dotrice", "dotrice"),
    LINOTTE("Linotte", "linotte"),
    POPPINS("Poppins", "poppins"),
    SANS_SERIF("Sans Serif", "sans_serif"),
    SERIF("Serif", "serif"),
    MONOSPACE("Monospace", "monospace");
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    fun family(): FontFamily = when (this) {
        SYSTEM -> FontFamily.Default
        SF_PRO -> FontFamily(
            Font(R.font.sf_pro_display_regular, FontWeight.Normal),
            Font(R.font.sf_pro_display_medium, FontWeight.Medium),
            Font(R.font.sf_pro_display_semibold, FontWeight.SemiBold),
            Font(R.font.sf_pro_display_bold, FontWeight.Bold),
            Font(R.font.sf_pro_display_heavy, FontWeight.Black),
        )
        DOTO -> FontFamily(
            Font(R.font.doto_regular, FontWeight.Normal),
            Font(R.font.doto_medium, FontWeight.Medium),
            Font(R.font.doto_semibold, FontWeight.SemiBold),
            Font(R.font.doto_bold, FontWeight.Bold),
            Font(R.font.doto_black, FontWeight.Black),
        )
        DOTO_ROUNDED -> FontFamily(
            Font(R.font.doto_rounded_regular, FontWeight.Normal),
            Font(R.font.doto_rounded_medium, FontWeight.Medium),
            Font(R.font.doto_rounded_semibold, FontWeight.SemiBold),
            Font(R.font.doto_rounded_bold, FontWeight.Bold),
            Font(R.font.doto_rounded_black, FontWeight.Black),
        )
        DOTRICE -> FontFamily(
            Font(R.font.dotrice_regular, FontWeight.Normal),
            Font(R.font.dotrice_bold, FontWeight.Bold),
        )
        LINOTTE -> FontFamily(Font(R.font.linotte))
        POPPINS -> FontFamily(
            Font(R.font.poppins_regular, FontWeight.Normal),
            Font(R.font.poppins_medium, FontWeight.Medium),
            Font(R.font.poppins_bold, FontWeight.Bold),
        )
        SANS_SERIF -> FontFamily.SansSerif
        SERIF -> FontFamily.Serif
        MONOSPACE -> FontFamily.Monospace
    }

    companion object {
        fun fromKey(key: String?): AppFont = entries.firstOrNull {
            it.key.equals(key, ignoreCase = true) || it.label.equals(key, ignoreCase = true)
        } ?: SYSTEM
    }
}
