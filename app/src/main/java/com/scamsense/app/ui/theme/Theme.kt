package com.scamsense.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.scamsense.app.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0A2472),
    secondary = Color(0xFF0E7490),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5F3FA),
    onSecondaryContainer = Color(0xFF083344),
    background = BackgroundLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = Color(0xFFEDF1F8),
    onSurfaceVariant = InkMutedLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFD),
    surfaceContainer = Color(0xFFF1F4FA),
    surfaceContainerHigh = Color(0xFFEAEFF7),
    outline = OutlineLight,
    outlineVariant = Color(0xFFE6EAF2),
    error = Color(0xFFC81E1E),
)

private val DarkColors = darkColorScheme(
    primary = BrandLight,
    onPrimary = Color(0xFF0A1F66),
    primaryContainer = Color(0xFF1C3A99),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFF67D4EA),
    onSecondary = Color(0xFF083344),
    secondaryContainer = Color(0xFF134E5E),
    onSecondaryContainer = Color(0xFFD5F3FA),
    background = BackgroundDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = Color(0xFF1B2640),
    onSurfaceVariant = InkMutedDark,
    surfaceContainerLowest = Color(0xFF0E1628),
    surfaceContainerLow = Color(0xFF111A2E),
    surfaceContainer = Color(0xFF162036),
    surfaceContainerHigh = Color(0xFF1B2640),
    outline = OutlineDark,
    outlineVariant = Color(0xFF223050),
    error = Color(0xFFF87171),
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun isAppInDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * @param textScale extra in-app text scaling on top of the system font size, so users
 * can enlarge text without changing the whole phone.
 */
@Composable
fun ScamSenseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    textScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    // Cap the combined scale so layouts stay usable at the system's largest font size.
    val combined = (density.fontScale * textScale).coerceAtMost(2.0f)
    CompositionLocalProvider(LocalDensity provides Density(density.density, combined)) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
