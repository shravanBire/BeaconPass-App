package com.example.beaconpass.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = ElectricCobalt,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = DeepSlate,
    onSecondary = Color.White,
    background = GhostWhite,
    onBackground = TextPrimary,
    surface = Color.White,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = TextMuted,
    outline = CardBorderSubtle,
    error = CrimsonError,
    errorContainer = CrimsonErrorContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCobalt,
    onPrimary = Color.White,
    secondary = SurfaceDark,
    background = DeepSlate,
    surface = SurfaceDark,
    onSurface = Color(0xFFF8FAFC),
    outline = Color(0xFF334155),
    error = CrimsonError
)

@Composable
fun BeaconPassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}