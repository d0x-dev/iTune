package com.darkxvenom.itune.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.darkxvenom.itune.R

enum class AppFont(val label: String, val key: String) {
    SYSTEM("System Default", "system"),
    SF_PRO("SF Pro Display", "sf_pro"),
    LINOTTE("Linotte", "linotte"),
    POPPINS("Poppins", "poppins"),
    SANS_SERIF("Sans Serif", "sans_serif"),
    SERIF("Serif", "serif"),
    MONOSPACE("Monospace", "monospace");

    fun family(): FontFamily = when (this) {
        SYSTEM -> FontFamily.Default
        SF_PRO -> FontFamily(
            Font(R.font.sf_pro_display_regular, FontWeight.Normal),
            Font(R.font.sf_pro_display_medium, FontWeight.Medium),
            Font(R.font.sf_pro_display_semibold, FontWeight.SemiBold),
            Font(R.font.sf_pro_display_bold, FontWeight.Bold),
            Font(R.font.sf_pro_display_heavy, FontWeight.Black),
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
