package com.darkxvenom.itune.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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

@Composable
fun ITuneTheme(
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

    MaterialTheme(
        colorScheme = AppleDarkColorScheme,
        typography = Typography,
        content = content
    )
}
