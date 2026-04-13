package com.voiddrop.app.presentation.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// VoidDrop Pure Black Theme - Maximum Contrast
private val VoidDarkColorScheme = darkColorScheme(
    primary = VoidWhite,
    onPrimary = VoidBlack,
    secondary = VoidWhite,
    onSecondary = VoidBlack,
    tertiary = VoidWhite,
    onTertiary = VoidBlack,
    background = VoidBlack,
    onBackground = VoidWhite,
    surface = VoidBlack,
    onSurface = VoidWhite,
    surfaceVariant = VoidBlack,
    onSurfaceVariant = VoidWhite,
    error = VoidWhite,
    onError = VoidBlack,
    outline = VoidWhite,
    outlineVariant = VoidGray
)

// VoidDrop Pure White Theme - Maximum Contrast (for light mode)
private val VoidLightColorScheme = lightColorScheme(
    primary = VoidBlack,
    onPrimary = VoidWhite,
    secondary = VoidBlack,
    onSecondary = VoidWhite,
    tertiary = VoidBlack,
    onTertiary = VoidWhite,
    background = VoidWhite,
    onBackground = VoidBlack,
    surface = VoidWhite,
    onSurface = VoidBlack,
    surfaceVariant = VoidWhite,
    onSurfaceVariant = VoidBlack,
    error = VoidBlack,
    onError = VoidWhite,
    outline = VoidBlack,
    outlineVariant = VoidGray
)

@Composable
fun VoidDropTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Disable dynamic color to maintain pure black/white theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Force VoidDrop monochrome theme regardless of system settings
        darkTheme -> VoidDarkColorScheme
        else -> VoidLightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}