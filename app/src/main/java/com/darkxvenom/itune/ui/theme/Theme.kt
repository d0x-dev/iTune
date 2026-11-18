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

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AppleDarkColorScheme = darkColorScheme(
    primary = AppleMusicRed,
    onPrimary = AppleTextPrimary,
    primaryContainer = AppleSurfaceElevated,
    onPrimaryContainer = AppleTextPrimary,
    secondary = AppleTextSecondary,
    onSecondary = AppleTextPrimary,
    background = AppleOledBlack,
    onBackground = AppleTextPrimary,
    surface = AppleSurface,
    onSurface = AppleTextPrimary,
    surfaceVariant = AppleSurfaceElevated,
    onSurfaceVariant = AppleTextSecondary,
    outline = AppleBorder
)
// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

@Composable
fun ITuneTheme(
    appFont: String = "system",
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
    }

    val appFontFamily = AppFont.fromKey(appFont).family()
    CompositionLocalProvider(LocalAppFontFamily provides appFontFamily) {
        MaterialTheme(
            colorScheme = AppleDarkColorScheme,
            typography = typographyForAppFont(appFont),
            content = content
        )
    }
}
