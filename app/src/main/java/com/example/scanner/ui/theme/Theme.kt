package com.example.scanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Dark-only gold color scheme.
private val ScannerColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = OnGold,
    primaryContainer = GoldContainer,
    onPrimaryContainer = OnGoldContainer,
    secondary = Gold,
    onSecondary = OnGold,
    tertiary = Gold,
    onTertiary = OnGold,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainer = SurfaceContainer,
    outline = Outline,
    error = ErrorRed,
    onError = OnGold,
)

@Composable
fun ScannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ScannerColorScheme,
        typography = ScannerTypography,
        content = content,
    )
}
