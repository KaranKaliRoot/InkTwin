package com.app.inktwin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    primaryContainer = Color(0xFF1A2C4A),
    secondary = Color(0xFFB4C7E0),
    secondaryContainer = Color(0xFF2B3D5E),
    tertiary = Color(0xFFCEC2F8),
    tertiaryContainer = Color(0xFF3B3262),
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF1E1E1E),
    background = Color(0xFF0A0A0A),
    onPrimary = Color(0xFF001D3D),
    onPrimaryContainer = Color(0xFFD0E3FF),
    onSecondary = Color(0xFF14263A),
    onSecondaryContainer = Color(0xFFD9E8FF),
    onTertiary = Color(0xFF221A4C),
    onTertiaryContainer = Color(0xFFE8DDFF),
    onSurface = Color(0xFFE6E1E5),
    onSurfaceVariant = Color(0xFFC4C7CC),
    outline = Color(0xFF8E9095),
    outlineVariant = Color(0xFF44474B),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF313033),
    inversePrimary = Color(0xFF2B5A8E),
    surfaceTint = Color(0xFF8AB4F8)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2B5A8E),
    primaryContainer = Color(0xFFD0E3FF),
    secondary = Color(0xFF4C627A),
    secondaryContainer = Color(0xFFD9E8FF),
    tertiary = Color(0xFF5B4F8A),
    tertiaryContainer = Color(0xFFE8DDFF),
    surface = Color(0xFFFAFAFA),
    surfaceVariant = Color(0xFFE0E3E8),
    background = Color(0xFFFFFFFF),
    onPrimary = Color(0xFFFFFFFF),
    onPrimaryContainer = Color(0xFF001D3D),
    onSecondary = Color(0xFFFFFFFF),
    onSecondaryContainer = Color(0xFF14263A),
    onTertiary = Color(0xFFFFFFFF),
    onTertiaryContainer = Color(0xFF221A4C),
    onSurface = Color(0xFF1C1B1F),
    onSurfaceVariant = Color(0xFF44474B),
    outline = Color(0xFF74777B),
    outlineVariant = Color(0xFFC4C7CC),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = Color(0xFF8AB4F8),
    surfaceTint = Color(0xFF2B5A8E)
)

@Composable
fun InkTwinTheme(
    darkTheme: Boolean = false, // We'll read from system later
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}