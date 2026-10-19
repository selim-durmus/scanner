package com.tuttoposto.scanner.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Black and gold, matching the other personal apps. Pure black backgrounds for the OLED panel.
val Gold = Color(0xFFD4AF37)
val GoldSoft = Color(0xFFE9CF74)
val GoldDeep = Color(0xFF3A3010)
val GoldWash = Color(0x26D4AF37)
val Ink = Color(0xFFECECEC)
val InkMuted = Color(0xFF9C9C9C)
val InkFaint = Color(0xFF6A6A6A)
val Line = Color(0xFF242424)
val Danger = Color(0xFFFF6B6B)

/** The paper behind pages; slightly warm so white pages don't glare against black. */
val Paper = Color(0xFFF4F1EA)

private val colors = darkColorScheme(
    primary = Gold,
    onPrimary = Color.Black,
    primaryContainer = GoldDeep,
    onPrimaryContainer = GoldSoft,
    secondary = GoldSoft,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2A2516),
    onSecondaryContainer = GoldSoft,
    tertiary = GoldSoft,
    background = Color.Black,
    onBackground = Ink,
    surface = Color.Black,
    onSurface = Ink,
    surfaceVariant = Color(0xFF161616),
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF202020),
    inverseSurface = Ink,
    inverseOnSurface = Color.Black,
    inversePrimary = Color(0xFF7A6216),
    outline = Color(0xFF3A3A3A),
    outlineVariant = Line,
    error = Danger,
    onError = Color.Black,
    scrim = Color(0xCC000000),
)

private val base = Typography()

private val typography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun ScannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes) {
        // Screens draw straight on the window (no Surface), so set the default text colour here.
        CompositionLocalProvider(LocalContentColor provides Ink, content = content)
    }
}
